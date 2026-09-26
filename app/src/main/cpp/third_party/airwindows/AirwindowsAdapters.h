#pragma once

namespace picolo {

void* createKPlate140();
void destroyKPlate140(void* instance);
void processKPlate140(void* instance, const float* left, const float* right, int frames,
                      float decayMs, float predelayMs, float character,
                      float* outLeft, float* outRight);

void* createTapeDelay2();
void destroyTapeDelay2(void* instance);
void processTapeDelay2(void* instance, const float* left, const float* right, int frames,
                       float timeMs, float feedback, float toneHz,
                       float* outLeft, float* outRight);

} // namespace picolo
