#include <jni.h>
#include <algorithm>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>
#include "llama.h"

namespace {
constexpr int context_size = 2048;
constexpr int batch_size = 128;
constexpr int max_reply_tokens = 384;

std::string read_bytes(JNIEnv * env, jbyteArray bytes) {
    std::string result(env->GetArrayLength(bytes), '\0');
    env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(result.size()),
                            reinterpret_cast<jbyte *>(result.data()));
    return result;
}

auto load_model(const std::string & path) {
    static const bool initialized = [] {
        // Keep inference diagnostics out of logcat.
        llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        llama_backend_init();
        return true;
    }();
    (void) initialized;

    auto params = llama_model_default_params();
    params.n_gpu_layers = 0;
    std::unique_ptr<llama_model, decltype(&llama_model_free)> model(
        llama_model_load_from_file(path.c_str(), params), llama_model_free);
    if (!model || !llama_model_has_decoder(model.get()) || llama_model_has_encoder(model.get())) {
        throw std::runtime_error("This file could not be opened as a supported text GGUF model.");
    }
    return model;
}

std::string format_prompt(const llama_model * model, const std::string & prompt) {
    const char * chat_template = llama_model_chat_template(model, nullptr);
    if (!chat_template || !*chat_template) {
        // Some Llama 3 fine-tunes omit tokenizer.chat_template from their GGUF.
        // Recognize the actual token layout before selecting its standard format.
        const auto * vocab = llama_model_get_vocab(model);
        if (llama_vocab_n_tokens(vocab) > 128009 &&
            std::strcmp(llama_vocab_get_text(vocab, 128000), "<|begin_of_text|>") == 0 &&
            std::strcmp(llama_vocab_get_text(vocab, 128006), "<|start_header_id|>") == 0 &&
            std::strcmp(llama_vocab_get_text(vocab, 128007), "<|end_header_id|>") == 0 &&
            std::strcmp(llama_vocab_get_text(vocab, 128009), "<|eot_id|>") == 0) {
            chat_template = "llama3";
        } else {
            throw std::runtime_error("Choose an instruct GGUF model with an embedded chat template.");
        }
    }
    const llama_chat_message message{"user", prompt.c_str()};
    std::vector<char> buffer(prompt.size() + 1024);
    int size = llama_chat_apply_template(chat_template, &message, 1, true, buffer.data(), buffer.size());
    if (size > static_cast<int>(buffer.size())) {
        buffer.resize(size);
        size = llama_chat_apply_template(chat_template, &message, 1, true, buffer.data(), buffer.size());
    }
    if (size <= 0 || size > static_cast<int>(buffer.size())) {
        throw std::runtime_error("This model's chat template is not supported. Try a Qwen2.5 Instruct GGUF.");
    }
    return {buffer.data(), static_cast<size_t>(size)};
}

std::string generate(const llama_model * model, const std::string & prompt) {
    const auto formatted = format_prompt(model, prompt);
    const auto * vocab = llama_model_get_vocab(model);
    auto params = llama_context_default_params();
    params.n_ctx = std::min(context_size, llama_model_n_ctx_train(model));
    params.n_batch = batch_size;
    params.n_ubatch = batch_size;
    params.n_threads = std::clamp(std::thread::hardware_concurrency(), 1u, 4u);
    params.n_threads_batch = params.n_threads;

    const int available = static_cast<int>(params.n_ctx) - max_reply_tokens;
    if (available <= 0) {
        throw std::runtime_error("This model's context is too small for HiddenNoir.");
    }
    std::vector<llama_token> tokens(available);
    const int count = llama_tokenize(vocab, formatted.data(), formatted.size(),
                                     tokens.data(), available, true, true);
    if (count < 0) {
        throw std::runtime_error("The conversation is too long for this model. Paste fewer messages.");
    }
    if (count == 0) {
        throw std::runtime_error("The model could not read the prompt.");
    }

    std::unique_ptr<llama_context, decltype(&llama_free)> context(
        llama_init_from_model(const_cast<llama_model *>(model), params), llama_free);
    if (!context) {
        throw std::runtime_error("Unable to allocate model memory. Try a smaller quantized GGUF.");
    }

    for (int offset = 0; offset < count; offset += batch_size) {
        auto batch = llama_batch_get_one(tokens.data() + offset, std::min(batch_size, count - offset));
        if (llama_decode(context.get(), batch) != 0) {
            throw std::runtime_error("The model could not process the conversation.");
        }
    }

    // Use structured output so small instruct models can populate the existing reply cards.
    constexpr auto grammar = R"(
root ::= "{" ws "\"replies\"" ws ":" ws "[" ws string (ws "," ws string){3} ws "]" ws "}"
ws ::= [ \t\n\r]{0,4}
string ::= "\"" char{1,120} "\""
char ::= [^"\\\x00-\x1F] | "\\" ["\\/bfnrt]
)";
    auto * constraint = llama_sampler_init_grammar(vocab, grammar, "root");
    if (!constraint) {
        throw std::runtime_error("Could not initialize structured reply generation.");
    }
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
        llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    llama_sampler_chain_add(sampler.get(), constraint);
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.92f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.8f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    std::vector<char> piece(256);
    for (int i = 0; i < max_reply_tokens; ++i) {
        llama_token token = llama_sampler_sample(sampler.get(), context.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        int length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (length < 0) {
            piece.resize(-length);
            length = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        }
        if (length > 0) output.append(piece.data(), length);
        if (llama_decode(context.get(), llama_batch_get_one(&token, 1)) != 0) {
            throw std::runtime_error("The model stopped while generating a reply.");
        }
    }
    return output;
}

void report_error(JNIEnv * env, const std::exception & error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_hiddennoir_app_NativeInference_validate(JNIEnv * env, jobject, jbyteArray path) {
    try {
        const auto model = load_model(read_bytes(env, path));
        format_prompt(model.get(), "Reply to this message.");
        if (llama_model_n_ctx_train(model.get()) <= max_reply_tokens) {
            throw std::runtime_error("This model's context is too small for HiddenNoir.");
        }
    } catch (const std::exception & error) {
        report_error(env, error);
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_hiddennoir_app_NativeInference_generate(JNIEnv * env, jobject, jbyteArray path, jbyteArray prompt) {
    try {
        const auto model = load_model(read_bytes(env, path));
        const auto output = generate(model.get(), read_bytes(env, prompt));
        const auto bytes = env->NewByteArray(static_cast<jsize>(output.size()));
        if (bytes) {
            env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(output.size()),
                                    reinterpret_cast<const jbyte *>(output.data()));
        }
        return bytes;
    } catch (const std::exception & error) {
        report_error(env, error);
        return nullptr;
    }
}
