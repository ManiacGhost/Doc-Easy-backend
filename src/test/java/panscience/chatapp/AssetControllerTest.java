package panscience.chatapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadThenListThenGetSummaryWorks() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "Spring Boot assignment content with Q and A context".getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/assets/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.assetType").value("DOCUMENT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode node = objectMapper.readTree(uploadResponse);
        long id = node.path("id").asLong();
        assertThat(id).isPositive();

        mockMvc.perform(get("/api/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());

        mockMvc.perform(get("/api/assets/{id}/summary", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(id))
                .andExpect(jsonPath("$.summary").isNotEmpty());
    }

    @Test
    void askTimestampsAndPlayLinkWork() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "audio.mp3",
                "audio/mpeg",
                "lecture talking about indexing and retrieval".getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/assets/upload").file(file))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(uploadResponse).path("id").asLong();

        mockMvc.perform(post("/api/assets/{id}/ask", id)
                        .contentType("application/json")
                        .content("{\"question\":\"what is discussed?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.timestamps[0].seconds").exists());

        mockMvc.perform(get("/api/assets/{id}/timestamps", id).param("topic", "indexing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamps[0].seconds").exists());

        mockMvc.perform(get("/api/assets/{id}/play-link", id).param("startSeconds", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startSeconds").value(42))
                .andExpect(jsonPath("$.playableUrl").value("/api/assets/" + id + "/stream?startSeconds=42"));
    }

    @Test
    void missingAssetReturns404() throws Exception {
        mockMvc.perform(get("/api/assets/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }
}

