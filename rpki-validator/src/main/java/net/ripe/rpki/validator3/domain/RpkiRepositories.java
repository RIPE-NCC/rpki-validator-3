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
package net.ripe.rpki.validator3.domain;

import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.constraints.ValidLocationURI;
import org.springframework.web.bind.annotation.PathVariable;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public interface RpkiRepositories {
    RpkiRepository register(@NotNull @Valid TrustAnchor trustAnchor, @NotNull @ValidLocationURI String uri, @NotNull RpkiRepository.Type type);

    Optional<RpkiRepository> findByURI(@NotNull @ValidLocationURI String uri);

    RpkiRepository get(long id);

    Stream<RpkiRepository> findAll(RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm, Sorting sorting, Paging paging);

    long countAll(RpkiRepository.Status optionalStatus, Long taId, boolean hideChildrenOfDownloadedParent, SearchTerm searchTerm);

    Map<RpkiRepository.Status, Long> countByStatus(@PathVariable Long taId, boolean hideChildrenOfDownloadedParent);

    default Stream<RpkiRepository> findAll(RpkiRepository.Status optionalStatus, Long taId) {
        return findAll(optionalStatus, taId, false, null, null, null);
    }

    default Stream<RpkiRepository> findAll(Long taId) {
        return findAll(null, taId, false, null, null, null);
    }

    Stream<RpkiRepository> findRsyncRepositories();

    void removeAllForTrustAnchor(TrustAnchor trustAnchor);
}
