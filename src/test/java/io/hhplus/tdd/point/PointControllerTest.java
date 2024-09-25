package io.hhplus.tdd.point;

import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserPointTable userPointTable;

    @BeforeEach
    void setUp() {
        long userId = 1L;
        UserPoint userPoint = new UserPoint(userId, 100L, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(userPoint);
    }

    @Test
    @DisplayName("유효한 ID가 제공되면 사용자 포인트를 반환한다.")
    void shouldReturnUserPointWhenValidIdIsProvided() throws Exception {
        // given
        long userId = 1L;

        // when & then
        mockMvc.perform(get("/point/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))  // 수정된 부분
                .andExpect(jsonPath("$.point").value(100L));
    }
}
