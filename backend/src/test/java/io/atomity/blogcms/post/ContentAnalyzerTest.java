package io.atomity.blogcms.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomity.blogcms.post.service.ContentAnalyzer;
import io.atomity.blogcms.shared.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentAnalyzerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode parse(String json) throws Exception {
        return mapper.readTree(json.replace('\'', '"'));
    }

    @Test
    void separatesBlocksSoWordsDontRunTogether() throws Exception {
        var analysis = ContentAnalyzer.analyze(parse("""
                {'type':'doc','content':[
                  {'type':'heading','attrs':{'level':2},'content':[{'type':'text','text':'Title'}]},
                  {'type':'paragraph','content':[{'type':'text','text':'one '},{'type':'text','text':'two','marks':[{'type':'bold'}]},
                                                 {'type':'hardBreak'},{'type':'text','text':'three'}]},
                  {'type':'bulletList','content':[{'type':'listItem','content':[{'type':'paragraph','content':[{'type':'text','text':'four'}]}]}]}
                ]}"""));
        assertThat(analysis.text()).isEqualTo("Title one two three four");
        assertThat(analysis.wordCount()).isEqualTo(5);
        assertThat(analysis.readingTimeMinutes()).isEqualTo(1);
    }

    @Test
    void emptyDocumentHasNoWordsAndZeroReadingTime() throws Exception {
        var analysis = ContentAnalyzer.analyze(parse("{'type':'doc','content':[{'type':'paragraph'}]}"));
        assertThat(analysis.wordCount()).isZero();
        assertThat(analysis.readingTimeMinutes()).isZero();
        assertThat(analysis.isEmpty()).isTrue();
    }

    @Test
    void collectsLibraryImages() throws Exception {
        UUID id = UUID.randomUUID();
        var analysis = ContentAnalyzer.analyze(parse(
                "{'type':'doc','content':[{'type':'image','attrs':{'src':'/api/v1/media/x/file','mediaId':'" + id + "'}},"
                        + "{'type':'image','attrs':{'src':'https://example.com/a.png'}}]}"));
        assertThat(analysis.imageIds()).containsExactly(id);
        assertThat(analysis.isEmpty()).isFalse();
    }

    @Test
    void collectsLibraryImageIdFromUrlWhenMediaIdIsMissing() throws Exception {
        UUID id = UUID.randomUUID();

        var analysis = ContentAnalyzer.analyze(parse(
                "{'type':'doc','content':[{'type':'image','attrs':{'src':'/api/v1/media/"
                        + id + "/file'}}]}"));

        assertThat(analysis.imageIds()).containsExactly(id);
    }

    @Test
    void doesNotInferMediaIdFromExternalImageUrl() throws Exception {
        var analysis = ContentAnalyzer.analyze(parse(
                "{'type':'doc','content':[{'type':'image','attrs':{'src':'https://example.com/a.png'}}]}"));

        assertThat(analysis.imageIds()).isEmpty();
    }

    @Test
    void rejectsUnsafeUrls() {
        assertThatThrownBy(() -> ContentAnalyzer.analyze(parse(
                "{'type':'doc','content':[{'type':'image','attrs':{'src':'data:image/svg+xml;base64,AAAA'}}]}")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("UNSAFE_IMAGE");
        assertThatThrownBy(() -> ContentAnalyzer.analyze(parse(
                "{'type':'doc','content':[{'type':'paragraph','content':[{'type':'text','text':'x',"
                        + "'marks':[{'type':'link','attrs':{'href':' JavaScript:alert(1)'}}]}]}]}")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("UNSAFE_LINK");
    }

    @Test
    void rejectsNonDocuments() {
        assertThatThrownBy(() -> ContentAnalyzer.analyze(parse("{'type':'paragraph'}")))
                .isInstanceOf(ApiException.class).extracting("errorCode").isEqualTo("INVALID_CONTENT");
        assertThatThrownBy(() -> ContentAnalyzer.analyze(parse("[1,2]"))).isInstanceOf(ApiException.class);
    }
}
