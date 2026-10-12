// Puente C mínimo entre Kotlin (JNA) y whisper.cpp.
// Evita mapear desde Kotlin las estructuras de parámetros de whisper.cpp, que cambian entre versiones.
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

void *dictado_whisper_init(const char *model_path) {
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = true;
    return whisper_init_from_file_with_params(model_path, cparams);
}

// Devuelve el texto completo (UTF-8, reservado con malloc) o NULL si hay error.
char *dictado_whisper_transcribe(void *ctx, const float *samples, int n_samples, const char *language, int n_threads) {
    if (!ctx || !samples || n_samples <= 0) return NULL;

    struct whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.language = language;
    p.translate = false;
    p.no_timestamps = true;
    p.n_threads = n_threads;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_special = false;
    p.print_timestamps = false;

    if (whisper_full((struct whisper_context *)ctx, p, samples, n_samples) != 0) return NULL;

    int n = whisper_full_n_segments((struct whisper_context *)ctx);
    size_t total = 1;
    for (int i = 0; i < n; i++) total += strlen(whisper_full_get_segment_text((struct whisper_context *)ctx, i));

    char *out = (char *)malloc(total);
    if (!out) return NULL;
    out[0] = '\0';
    for (int i = 0; i < n; i++) strcat(out, whisper_full_get_segment_text((struct whisper_context *)ctx, i));
    return out;
}

void dictado_whisper_free_text(char *text) { free(text); }

void dictado_whisper_free(void *ctx) {
    if (ctx) whisper_free((struct whisper_context *)ctx);
}
