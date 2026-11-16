// JNI bridge between pelc's Kotlin shaping API and HarfBuzz.
//
// Design notes:
//  - Fonts are created from in-memory bytes (hb_blob with HB_MEMORY_MODE_DUPLICATE)
//    so the JVM array can be released immediately.
//  - All coordinates returned to Kotlin are in *pixels* for the requested font
//    size: HarfBuzz is run with a 64x scale and results are divided by 64 to
//    keep sub-pixel precision.
//  - Glyph outlines are always extracted at a fixed em size of 100 px
//    (OutlineOps.BASE_EM) so they can be cached and re-scaled by callers.

#include <jni.h>
#include <hb.h>
#include <hb-ot.h>

#include <vector>
#include <cstdint>

namespace {

constexpr float kInv64 = 1.0f / 64.0f;

// Outline builder emitting pelc's outline command stream (see OutlineOps).
// opcodes: 0 move, 1 line, 2 quad, 3 cubic, 4 close.
class OutlineBuilder {
public:
    std::vector<float> out;

    void move(float x, float y) {
        out.push_back(0.0f);
        pushPoint(x, y);
    }
    void line(float x, float y) {
        out.push_back(1.0f);
        pushPoint(x, y);
    }
    void quad(float cx, float cy, float x, float y) {
        out.push_back(2.0f);
        pushPoint(cx, cy);
        pushPoint(x, y);
    }
    void cubic(float c1x, float c1y, float c2x, float c2y, float x, float y) {
        out.push_back(3.0f);
        pushPoint(c1x, c1y);
        pushPoint(c2x, c2y);
        pushPoint(x, y);
    }
    void close() {
        out.push_back(4.0f);
    }

private:
    void pushPoint(float x, float y) {
        out.push_back(x * kInv64);
        out.push_back(y * kInv64);
    }
};

void drawMoveTo(hb_draw_funcs_t*, void* drawData, hb_draw_state_t*, float toX, float toY, void*) {
    static_cast<OutlineBuilder*>(drawData)->move(toX, toY);
}

void drawLineTo(hb_draw_funcs_t*, void* drawData, hb_draw_state_t*, float toX, float toY, void*) {
    static_cast<OutlineBuilder*>(drawData)->line(toX, toY);
}

void drawQuadraticTo(hb_draw_funcs_t*, void* drawData, hb_draw_state_t*,
                     float ctrlX, float ctrlY, float toX, float toY, void*) {
    static_cast<OutlineBuilder*>(drawData)->quad(ctrlX, ctrlY, toX, toY);
}

void drawCubicTo(hb_draw_funcs_t*, void* drawData, hb_draw_state_t*,
                 float c1x, float c1y, float c2x, float c2y, float toX, float toY, void*) {
    static_cast<OutlineBuilder*>(drawData)->cubic(c1x, c1y, c2x, c2y, toX, toY);
}

void drawClose(hb_draw_funcs_t*, void* drawData, hb_draw_state_t*, void*) {
    static_cast<OutlineBuilder*>(drawData)->close();
}

hb_draw_funcs_t* drawFuncs() {
    static hb_draw_funcs_t* funcs = nullptr;
    if (funcs == nullptr) {
        hb_draw_funcs_t* created = hb_draw_funcs_create();
        hb_draw_funcs_set_move_to_func(created, drawMoveTo, nullptr, nullptr);
        hb_draw_funcs_set_line_to_func(created, drawLineTo, nullptr, nullptr);
        hb_draw_funcs_set_quadratic_to_func(created, drawQuadraticTo, nullptr, nullptr);
        hb_draw_funcs_set_cubic_to_func(created, drawCubicTo, nullptr, nullptr);
        // HarfBuzz ≥ 7.0: نام تابع به close_path تغییر کرده است
        hb_draw_funcs_set_close_path_func(created, drawClose, nullptr, nullptr);
        hb_draw_funcs_make_immutable(created);
        funcs = created;
    }
    return funcs;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_ir_pelc_core_shaping_HarfBuzzJni_createFont(JNIEnv* env, jobject, jbyteArray data, jint faceIndex) {
    if (data == nullptr) return 0;
    const jsize length = env->GetArrayLength(data);
    if (length <= 0) return 0;

    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    hb_blob_t* blob = hb_blob_create(reinterpret_cast<const char*>(bytes), length,
                                     HB_MEMORY_MODE_DUPLICATE, nullptr, nullptr);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
    if (blob == nullptr) return 0;

    hb_face_t* face = hb_face_create(blob, static_cast<unsigned int>(faceIndex));
    hb_blob_destroy(blob);
    if (face == nullptr || hb_face_get_glyph_count(face) == 0) {
        if (face != nullptr) hb_face_destroy(face);
        return 0;
    }

    hb_font_t* font = hb_font_create(face);
    hb_face_destroy(face);  // the font holds its own reference to the face
    return reinterpret_cast<jlong>(font);
}

extern "C" JNIEXPORT void JNICALL
Java_ir_pelc_core_shaping_HarfBuzzJni_destroyFont(JNIEnv*, jobject, jlong fontPtr) {
    if (fontPtr != 0) hb_font_destroy(reinterpret_cast<hb_font_t*>(fontPtr));
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_ir_pelc_core_shaping_HarfBuzzJni_shape(JNIEnv* env, jobject, jstring text, jlong fontPtr,
                                            jboolean rtl, jfloat fontSize, jintArray features) {
    auto* font = reinterpret_cast<hb_font_t*>(fontPtr);
    if (font == nullptr || text == nullptr) return nullptr;

    const jsize charCount = env->GetStringLength(text);
    if (charCount <= 0) {
        jfloatArray empty = env->NewFloatArray(1);
        if (empty != nullptr) {
            const jfloat zero = 0.0f;
            env->SetFloatArrayRegion(empty, 0, 1, &zero);
        }
        return empty;
    }

    const jchar* chars = env->GetStringChars(text, nullptr);
    if (chars == nullptr) return nullptr;

    hb_buffer_t* buffer = hb_buffer_create();
    hb_buffer_set_cluster_level(buffer, HB_BUFFER_CLUSTER_LEVEL_MONOTONE_CHARACTERS);
    hb_buffer_add_utf16(buffer, reinterpret_cast<const uint16_t*>(chars),
                        static_cast<unsigned int>(charCount), 0, -1);
    env->ReleaseStringChars(text, chars);

    hb_buffer_guess_segment_properties(buffer);
    hb_buffer_set_language(buffer, hb_language_from_string("fa", -1));
    hb_buffer_set_direction(buffer, rtl ? HB_DIRECTION_RTL : HB_DIRECTION_LTR);

    std::vector<hb_feature_t> hbFeatures;
    if (features != nullptr) {
        const jsize count = env->GetArrayLength(features);
        jint* raw = env->GetIntArrayElements(features, nullptr);
        for (jsize i = 0; i + 3 < count; i += 4) {
            hb_feature_t f;
            f.tag = static_cast<hb_tag_t>(raw[i]);
            f.value = static_cast<unsigned int>(raw[i + 1]);
            f.start = raw[i + 2] < 0 ? 0u : static_cast<unsigned int>(raw[i + 2]);
            f.end = raw[i + 3] < 0 ? HB_FEATURE_GLOBAL_END : static_cast<unsigned int>(raw[i + 3]);
            hbFeatures.push_back(f);
        }
        env->ReleaseIntArrayElements(features, raw, JNI_ABORT);
    }

    // 64x scale keeps sub-pixel precision; results are divided by 64 below.
    const int scale = static_cast<int>(fontSize * 64.0f);
    hb_font_set_scale(font, scale, scale);
    hb_shape(font, buffer, hbFeatures.data(), static_cast<unsigned int>(hbFeatures.size()));

    unsigned int glyphCount = 0;
    hb_glyph_info_t* infos = hb_buffer_get_glyph_infos(buffer, &glyphCount);
    hb_glyph_position_t* positions = hb_buffer_get_glyph_positions(buffer, &glyphCount);

    const jsize outSize = 1 + static_cast<jsize>(glyphCount) * 6;
    jfloatArray result = env->NewFloatArray(outSize);
    if (result != nullptr) {
        std::vector<float> out;
        out.reserve(static_cast<size_t>(outSize));
        out.push_back(static_cast<float>(glyphCount));
        for (unsigned int i = 0; i < glyphCount; ++i) {
            out.push_back(static_cast<float>(infos[i].codepoint));
            out.push_back(static_cast<float>(infos[i].cluster));
            out.push_back(positions[i].x_advance * kInv64);
            out.push_back(positions[i].y_advance * kInv64);
            out.push_back(positions[i].x_offset * kInv64);
            out.push_back(positions[i].y_offset * kInv64);
        }
        env->SetFloatArrayRegion(result, 0, outSize, out.data());
    }
    hb_buffer_destroy(buffer);
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_ir_pelc_core_shaping_HarfBuzzJni_glyphOutline(JNIEnv* env, jobject, jlong fontPtr, jint glyphId) {
    auto* font = reinterpret_cast<hb_font_t*>(fontPtr);
    if (font == nullptr || glyphId < 0) return nullptr;

    // Extract outlines at the fixed base em (100 px) so they stay cacheable.
    hb_font_set_scale(font, 6400, 6400);

    OutlineBuilder builder;
    hb_font_draw_glyph(font, static_cast<unsigned int>(glyphId), drawFuncs(), &builder);

    if (builder.out.empty()) return env->NewFloatArray(0);
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(builder.out.size()));
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(builder.out.size()), builder.out.data());
    }
    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_ir_pelc_core_shaping_HarfBuzzJni_gsubFeatureTags(JNIEnv* env, jobject, jlong fontPtr) {
    jclass stringClass = env->FindClass("java/lang/String");

    auto* font = reinterpret_cast<hb_font_t*>(fontPtr);
    if (font == nullptr || stringClass == nullptr) return env->NewObjectArray(0, stringClass, nullptr);

    hb_face_t* face = hb_font_get_face(font);
    hb_tag_t tags[256];
    unsigned int count = 256;
    hb_ot_layout_table_get_feature_tags(face, HB_OT_TAG_GSUB, 0, &count, tags);

    jobjectArray result = env->NewObjectArray(static_cast<jsize>(count), stringClass, nullptr);
    for (unsigned int i = 0; i < count && result != nullptr; ++i) {
        char tag[5] = {0, 0, 0, 0, 0};
        hb_tag_to_string(tags[i], tag);
        jstring s = env->NewStringUTF(tag);
        env->SetObjectArrayElement(result, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return result;
}
