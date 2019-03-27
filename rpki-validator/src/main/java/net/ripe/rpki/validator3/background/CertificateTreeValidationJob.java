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
package net.ripe.rpki.validator3.background;

import lombok.Getter;
import lombok.Setter;
import net.ripe.rpki.validator3.domain.validation.CertificateTreeValidationService;
import net.ripe.rpki.validator3.storage.data.TrustAnchor;
import net.ripe.rpki.validator3.storage.data.validation.CertificateTreeValidationRun;
import net.ripe.rpki.validator3.storage.stores.Id;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class CertificateTreeValidationJob implements Job {

    public static final String TRUST_ANCHOR_ID_KEY = "trustAnchorId";

    @Autowired
    private CertificateTreeValidationService validationService;

    @Getter
    @Setter
    private long trustAnchorId;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        validationService.validate(trustAnchorId);
    }

    static JobDetail buildJob(TrustAnchor trustAnchor) {
        return JobBuilder.newJob(CertificateTreeValidationJob.class)
                .storeDurably()
                .withIdentity(getJobKey(trustAnchor))
                .usingJobData(TRUST_ANCHOR_ID_KEY, Id.asLong(trustAnchor.getId()))
                .build();
    }

    static JobKey getJobKey(TrustAnchor trustAnchor) {
        return new JobKey(String.format("%s#%s#%d", CertificateTreeValidationRun.TYPE,
                trustAnchor.getName(), Id.asLong(trustAnchor.getId())));
    }
}
