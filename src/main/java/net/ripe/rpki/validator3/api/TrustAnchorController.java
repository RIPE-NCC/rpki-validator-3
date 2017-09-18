package net.ripe.rpki.validator3.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/trust-anchors")
public class TrustAnchorController {
    @RequestMapping(method = RequestMethod.GET)
    public String index() {
        return "The index";
    }
}
