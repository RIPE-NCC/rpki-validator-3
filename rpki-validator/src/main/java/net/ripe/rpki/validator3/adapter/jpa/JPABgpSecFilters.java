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
package net.ripe.rpki.validator3.adapter.jpa;

import com.querydsl.jpa.impl.JPAQuery;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.BgpSecFilter;
import net.ripe.rpki.validator3.domain.BgpSecFilters;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.stream.Stream;

import static net.ripe.rpki.validator3.domain.querydsl.QBgpSecFilter.bgpSecFilter;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPABgpSecFilters extends JPARepository<BgpSecFilter> implements BgpSecFilters {

    protected JPABgpSecFilters() {
        super(bgpSecFilter);
    }

    @Override
    public Stream<BgpSecFilter> find(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        JPAQuery<BgpSecFilter> query = select();
        applySearchTerm(query, searchTerm);
        applySorting(sorting, query);
        applyPaging(query, paging);
        return stream(query);
    }

    private void applySorting(Sorting sorting, JPAQuery<BgpSecFilter> query) {
        // TODO Implement when required
    }

    private void applySearchTerm(JPAQuery<BgpSecFilter> query, SearchTerm searchTerm) {
        // TODO Implement when required
    }

    @Override
    public long count(SearchTerm searchTerm) {
        return select().fetchCount();
    }
}
