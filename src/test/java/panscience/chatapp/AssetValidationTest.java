package panscience.chatapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AssetValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void emptyUploadReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/assets/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File is required for upload"));
    }

    @Test
    void blankQuestionReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        String uploadResponse = mockMvc.perform(multipart("/api/assets/upload").file(file))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .build()
                .readTree(uploadResponse)
                .path("id")
                .asLong();

        mockMvc.perform(post("/api/assets/{id}/ask", id)
                        .contentType("application/json")
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void blankTopicReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/assets/{id}/timestamps", 1L).param("topic", ""))
                .andExpect(status().isBadRequest());
    }
}

