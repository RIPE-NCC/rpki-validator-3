import {ITrustAnchor} from "./trust-anchor";

export interface ITrustAnchorsRespons {
    links : {
        self: string;
    }
    data: ITrustAnchor[];
}