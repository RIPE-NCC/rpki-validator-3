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

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import net.ripe.ipresource.IpResourceType;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import net.ripe.rpki.validator3.domain.RoaPrefixAssertion;
import net.ripe.rpki.validator3.domain.RoaPrefixAssertions;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.stream.Stream;

import static net.ripe.rpki.validator3.domain.querydsl.QRoaPrefixAssertion.roaPrefixAssertion;

@Repository
@Transactional(Transactional.TxType.REQUIRED)
public class JPARoaPrefixAssertions extends JPARepository<RoaPrefixAssertion> implements RoaPrefixAssertions {

    protected JPARoaPrefixAssertions() {
        super(roaPrefixAssertion);
    }

    @Override
    public Stream<RoaPrefixAssertion> find(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        JPAQuery<RoaPrefixAssertion> query = select();
        applySearchTerm(query, searchTerm);
        applySorting(sorting, query);
        applyPaging(query, paging);
        return stream(query);
    }

    @Override
    public long count(SearchTerm searchTerm) {
        return applySearchTerm(select(), searchTerm).fetchCount();
    }

    private JPAQuery<RoaPrefixAssertion> applySearchTerm(JPAQuery<RoaPrefixAssertion> query, SearchTerm searchTerm) {
        if (searchTerm == null) {
            return query;
        }

        if (searchTerm.asAsn() != null) {
            query.where(roaPrefixAssertion.asn.eq(searchTerm.asAsn().longValue()));
        } else if (searchTerm.asIpRange() != null) {
            ComparableExpression<BigDecimal> begin = Expressions.asComparable(new BigDecimal(searchTerm.asIpRange().getStart().getValue()));
            ComparableExpression<BigDecimal> end = Expressions.asComparable(new BigDecimal(searchTerm.asIpRange().getEnd().getValue()));

            query.where(roaPrefixAssertion.prefixFamily.eq((byte) (searchTerm.asIpRange().getType() == IpResourceType.IPv4 ? 4 : 6)));
            query.where(
                roaPrefixAssertion.prefixBegin.between(begin, end)
                    .or(roaPrefixAssertion.prefixEnd.between(begin, end))
                    .or(begin.between(roaPrefixAssertion.prefixBegin, roaPrefixAssertion.prefixEnd))
            );
        } else {
            query.where(roaPrefixAssertion.comment.likeIgnoreCase(searchTerm.asString()));
        }
        return query;
    }

    private JPAQuery<RoaPrefixAssertion> applySorting(Sorting sorting, JPAQuery<RoaPrefixAssertion> query) {
        return query.orderBy(toOrderSpecifier(sorting));
    }

    private OrderSpecifier<?> toOrderSpecifier(Sorting sorting) {
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.ASN, Sorting.Direction.ASC);
        }

        Expression<? extends Comparable> column;
        switch (sorting.getBy()) {
            case PREFIX:
                column = roaPrefixAssertion.prefix;
                break;
            case COMMENT:
                column = roaPrefixAssertion.comment;
                break;
            case MAXIMUMLENGTH:
                column = roaPrefixAssertion.maximumLength;
                break;
            case ASN:
            default:
                column = roaPrefixAssertion.asn;
                break;
        }

        Order order = sorting.getDirection() == Sorting.Direction.DESC ? Order.DESC : Order.ASC;
        return new OrderSpecifier<>(order, column);
    }
}
