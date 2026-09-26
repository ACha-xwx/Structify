package com.feng.dsagent.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AsciiSafeStringSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** The whole point: a learner's Chinese output must survive the edge and still read as Chinese. */
    @Test
    void escapesNonAsciiYetParsesBackToTheSameText() throws Exception {
        RunCodeResponse response = new RunCodeResponse("c", "success", "输入的两倍是 42\n", "错误信息", 1468L, null);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json.chars()).allMatch(code -> code >= 0x20 && code <= 0x7e);
        assertThat(json).contains("\\u").doesNotContain("中文").doesNotContain("错误");

        RunCodeResponse parsed = objectMapper.readValue(json, RunCodeResponse.class);
        assertThat(parsed.stdout()).isEqualTo("输入的两倍是 42\n");
        assertThat(parsed.stderr()).isEqualTo("错误信息");
    }

    @Test
    void keepsAsciiOutputByteForByte() throws Exception {
        RunCodeResponse response = new RunCodeResponse("c", "success", "hi\n", "", 900L, null);

        assertThat(objectMapper.writeValueAsString(response))
            .isEqualTo("{\"language\":\"c\",\"status\":\"success\",\"stdout\":\"hi\\n\",\"stderr\":\"\",\"durationMs\":900,\"runId\":null}");
    }

    /** Quotes, backslashes and tabs are escaped the way JSON requires, not left to break the document. */
    @Test
    void escapesTheCharactersJsonRequires() throws Exception {
        assertThat(AsciiSafeStringSerializer.escape("a\"b\\c\td"))
            .isEqualTo("a\\\"b\\\\c\\td");

        RunCodeResponse response = new RunCodeResponse("c", "success", "say \"hi\"\t\\", "", 1L, null);
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(response), RunCodeResponse.class).stdout())
            .isEqualTo("say \"hi\"\t\\");
    }

    /** An emoji is a surrogate pair; escaping each char is how JSON carries a code point outside the BMP. */
    @Test
    void escapesSurrogatePairsSoEmojiSurvive() throws Exception {
        RunCodeResponse response = new RunCodeResponse("c", "success", "done \uD83D\uDE00", "", 1L, null);

        String json = objectMapper.writeValueAsString(response);
        assertThat(json.chars()).allMatch(code -> code >= 0x20 && code <= 0x7e);
        assertThat(objectMapper.readValue(json, RunCodeResponse.class).stdout()).isEqualTo("done \uD83D\uDE00");
    }

    /** CompilerService never hands out a null stream, and Jackson writes null without asking us. */
    @Test
    void leavesNullToJackson() throws Exception {
        RunCodeResponse response = new RunCodeResponse("c", "success", null, null, 1L, null);

        assertThat(objectMapper.writeValueAsString(response)).contains("\"stdout\":null", "\"stderr\":null");
    }

    /** RunId and status are untouched: only program output is ASCII-safe. */
    @Test
    void leavesTheOtherFieldsAlone() throws Exception {
        RunCodeResponse response = new RunCodeResponse("c", "compile_error", "", "", 12L, "run-42");

        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(response), RunCodeResponse.class))
            .isEqualTo(response);
    }
}
