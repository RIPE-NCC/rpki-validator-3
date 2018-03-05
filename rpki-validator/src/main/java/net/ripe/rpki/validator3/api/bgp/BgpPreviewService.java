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
package net.ripe.rpki.validator3.api.bgp;

import com.google.common.collect.ImmutableList;
import lombok.Value;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.SearchTerm;
import net.ripe.rpki.validator3.api.Sorting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BgpPreviewService {

    @Autowired
    private BgpRisDownloader bgpRisDownloader;

    private ImmutableList<BgpRisDump> bgpRisDumps = ImmutableList.of(
        BgpRisDump.of(
            "http://www.ris.ripe.net/dumps/riswhoisdump.IPv4.gz",
            null,
            Collections.emptyList()
        ),
        BgpRisDump.of(
            "http://www.ris.ripe.net/dumps/riswhoisdump.IPv6.gz",
            null,
            Collections.emptyList()
        )
    );

    private ImmutableList<BgpPreviewEntry> data = ImmutableList.of();

    public synchronized BgpPreviewResult find(SearchTerm searchTerm, Sorting sorting, Paging paging) {
        if (paging == null) {
            paging = Paging.of(0, Integer.MAX_VALUE);
        }
        if (sorting == null) {
            sorting = Sorting.of(Sorting.By.PREFIX, Sorting.Direction.ASC);
        }
        Stream<BgpPreviewController.BgpPreview> entries = data
            .stream()
            .filter(entry -> entry.matches(searchTerm))
            .sorted(bgpPreviewEntryComparator(sorting))
            .skip(paging.getStartFrom())
            .limit(paging.getPageSize())
            .map(entry -> new BgpPreviewController.BgpPreview(
                entry.getOrigin().toString(),
                entry.getPrefix().toString(),
                entry.getValidity()
            ));
        return BgpPreviewResult.of(data.size(), entries);
    }

    private Comparator<? super BgpPreviewEntry> bgpPreviewEntryComparator(Sorting sorting) {
        Comparator<BgpPreviewEntry> columns;
        switch (sorting.getBy()) {
            case ASN:
                columns = Comparator.comparing(BgpPreviewEntry::getOrigin)
                    .thenComparing(Comparator.comparing(BgpPreviewEntry::getPrefix))
                    .thenComparing(Comparator.comparing(BgpPreviewEntry::getValidity));
                break;
            case VALIDITY:
                columns = Comparator.comparing(BgpPreviewEntry::getValidity)
                    .thenComparing(Comparator.comparing(BgpPreviewEntry::getPrefix))
                    .thenComparing(Comparator.comparing(BgpPreviewEntry::getOrigin));
                break;
            case TA:
            default:
                columns = Comparator.comparing(BgpPreviewEntry::getPrefix)
                    .thenComparing(Comparator.comparing(BgpPreviewEntry::getOrigin))
                    .thenComparing(Comparator.comparing(BgpPreviewEntry::getValidity));
        }
        return sorting.getDirection() == Sorting.Direction.DESC ? columns.reversed() : columns;
    }

    public synchronized void updateBgpRisDump(Collection<BgpRisDump> updated) {
        // TODO implement actual validity checking logic
        ImmutableList.Builder<BgpPreviewEntry> builder = ImmutableList.builder();
        for (BgpRisDump dump : updated) {
            for (BgpRisEntry entry : dump.getEntries()) {
                builder.add(new BgpPreviewEntry(
                    entry.getOrigin(),
                    entry.getPrefix(),
                    "UNKNOWN"
                ));
            }
        }

        bgpRisDumps = ImmutableList.copyOf(updated);
        data = builder.build();
    }

    public BgpPreviewController.BgpPreview validity(Asn asn, IpRange prefix) {
        return null;
    }

    @Value(staticConstructor = "of")
    public static class BgpPreviewResult {
        int totalCount;
        Stream<BgpPreviewController.BgpPreview> data;
    }

    @Value
    private static class BgpPreviewEntry {
        Asn origin;
        IpRange prefix;
        String validity;

        public boolean matches(SearchTerm searchTerm) {
            if (searchTerm == null) {
                return true;
            }
            if (searchTerm.asAsn() != null && origin.equals(searchTerm.asAsn())) {
                return true;
            }
            if (searchTerm.asIpRange() != null && prefix.overlaps(searchTerm.asIpRange())) {
                return true;
            }
            return validity.equalsIgnoreCase(searchTerm.asString());
        }

    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 600_0000L)
    private void downloadRisPreview() {
        updateBgpRisDump(bgpRisDumps.stream().map(bgpRisDownloader::fetch).collect(Collectors.toList()));
    }
}
