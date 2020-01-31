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
package net.ripe.rpki.validator3.api;

import net.ripe.rpki.validator3.IntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@IntegrationTest
public class AngularRoutesTest {
    private MockMvc mvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Before
    public void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void should_return_404_for_rtr_server_cache_endpoint() throws Exception {
        ResultActions result = mvc.perform(
                get("/cache")
        );

        result.andExpect(status().isNotFound());
    }

    @Test
    public void should_return_404_for_rtr_server_clients_endpoint() throws Exception {
        ResultActions result = mvc.perform(
                get("/clients")
        );

        result.andExpect(status().isNotFound());
    }

    @Test
    public void should_return_404_for_unknown_api_subpath() throws Exception {
        ResultActions result = mvc.perform(
                get("/api/foobar")
        );

        result.andExpect(status().isNotFound());
    }

    @Test
    public void should_return_404_for_file_with_period() throws Exception {

        ResultActions result = mvc.perform(
                get("/file.json").accept(MediaType.TEXT_HTML_VALUE)
        );

        result.andExpect(status().isNotFound());
    }

    @Test
    public void should_allow_negated_words_in_subdir() throws Exception {
        ResultActions result = mvc.perform(
                get("/non-negated/cache").accept(MediaType.TEXT_HTML_VALUE)
        );

        result.andExpect(status().isOk());
    }

    @Test
    public void should_return_index_html_for_path_containing_negated_word() throws Exception {

        ResultActions result = mvc.perform(
                get("/cache-docs").accept(MediaType.TEXT_HTML_VALUE)
        );

        result.andExpect(status().isOk());
    }

    @Test
    public void should_return_index_html_for_multiple_slashes() throws Exception {
        ResultActions result = mvc.perform(
                get("////foo-bar/baz").accept(MediaType.TEXT_HTML_VALUE)
        );

        result.andExpect(status().isOk());
    }
    @Test
    public void should_return_index_html_for_root() throws Exception {

        ResultActions result = mvc.perform(
                get("/").accept(MediaType.TEXT_HTML_VALUE)
        );

        result.andExpect(status().isOk());
    }
}
