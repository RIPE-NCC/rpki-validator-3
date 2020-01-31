/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.api.trustanchors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ripe.rpki.validator3.IntegrationTest;
import net.ripe.rpki.validator3.api.ValidatorApi;
import net.ripe.rpki.validator3.api.ApiCommand;
import net.ripe.rpki.validator3.api.ApiResponse;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@IntegrationTest
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
            post("/api/trust-anchors")
                .accept(ValidatorApi.API_MIME_TYPE)
                .contentType(ValidatorApi.API_MIME_TYPE)
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
            .andExpect(content().contentType(ValidatorApi.API_MIME_TYPE));

        ApiResponse<TrustAnchorResource> response = addTrustAnchorResponse(result);
        assertThat(response.getData()).isNotNull();

        TrustAnchorResource resource = response.getData();

        Link selfRel = resource.getLinks().getLink("self");
        mvc.perform(
            get(selfRel.getHref())
                .accept(ValidatorApi.API_MIME_TYPE)
        )
            .andExpect(status().isOk())
            .andExpect(content().contentType(ValidatorApi.API_MIME_TYPE))
            .andExpect(jsonPath("$.data.name").value(TEST_CA_NAME));
    }

    @Test
    public void should_fail_on_invalid_request() throws Exception {
        ResultActions result = mvc.perform(
            post("/api/trust-anchors")
                .accept(ValidatorApi.API_MIME_TYPE)
                .contentType(ValidatorApi.API_MIME_TYPE)
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
            .andExpect(content().contentType(ValidatorApi.API_MIME_TYPE));

        ApiResponse<TrustAnchorResource> response = addTrustAnchorResponse(result);

        assertThat(response.getErrors()).isNotEmpty();
    }

    private ApiResponse<TrustAnchorResource> addTrustAnchorResponse(ResultActions result) throws java.io.IOException {
        String contentAsString = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(contentAsString, new TypeReference<ApiResponse<TrustAnchorResource>>() {
        });
    }

}
