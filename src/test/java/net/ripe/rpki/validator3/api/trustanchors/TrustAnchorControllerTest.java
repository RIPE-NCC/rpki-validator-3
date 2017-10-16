package net.ripe.rpki.validator3.api.trustanchors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ripe.rpki.validator3.api.Api;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.domain.TrustAnchor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.hateoas.Link;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TrustAnchorControllerTest {


    private static final String TEST_CA_NAME = "Test CA";

    private MockMvc mvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TrustAnchorController subject;

    @Before
    public void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void should_add_trust_anchor() throws Exception {
        ResultActions result = mvc.perform(
            post("/trust-anchors")
                .accept(Api.API_MIME_TYPE)
                .contentType(Api.API_MIME_TYPE)
                .content(objectMapper.writeValueAsString(ApiCommand.of(AddTrustAnchor.builder()
                    .type(TrustAnchor.TYPE)
                    .name(TEST_CA_NAME)
                    .locations(Arrays.asList("rsync://example.com/rpki"))
                    .subjectPublicKeyInfo("jdfakljkldf;adsfjkdsfkl;nasdjfnsldajfklsd;ajfk;ljdsakjfkla;sdhfkjdshfkljadsl;kjfdklfjdaksl;jdfkl;jafkldjsfkl;adjsfkl;adjsf;lkjkl;dj;adskjfdljadjbkfbkjblafkjdfbasfjlka")
                    .build()
                )))
        );

        result
            .andExpect(status().isCreated())
            .andExpect(content().contentType(Api.API_MIME_TYPE));

        ApiResponse<TrustAnchorResource> response = addTrustAnchorResponse(result);
        assertThat(response.getData()).isNotNull();

        TrustAnchorResource resource = response.getData();

        Link selfRel = resource.getLinks().getLink("self");
        mvc.perform(
            get(selfRel.getHref())
                .accept(Api.API_MIME_TYPE)
        )
            .andExpect(status().isOk())
            .andExpect(content().contentType(Api.API_MIME_TYPE))
            .andExpect(jsonPath("$.data.name").value(TEST_CA_NAME));
    }

    @Test
    public void should_fail_on_invalid_request() throws Exception {
        ResultActions result = mvc.perform(
            post("/trust-anchors")
                .accept(Api.API_MIME_TYPE)
                .contentType(Api.API_MIME_TYPE)
                .content(objectMapper.writeValueAsString(ApiCommand.of(AddTrustAnchor.builder()
                    .type(TrustAnchor.TYPE)
                    .name(TEST_CA_NAME)
                    .locations(Arrays.asList("invalid-location"))
                    .subjectPublicKeyInfo("public key info too short")
                    .build()
                )))
        );

        result
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(Api.API_MIME_TYPE));

        ApiResponse<TrustAnchorResource> response = addTrustAnchorResponse(result);

        assertThat(response.getErrors()).isNotEmpty();
    }

    private ApiResponse<TrustAnchorResource> addTrustAnchorResponse(ResultActions result) throws java.io.IOException {
        String contentAsString = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(contentAsString, new TypeReference<ApiResponse<TrustAnchorResource>>() {
        });
    }

}
