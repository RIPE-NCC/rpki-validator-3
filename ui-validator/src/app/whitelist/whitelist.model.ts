import {IBgp} from "../bgp-preview/bgp.model";

export interface IWhitelistEntry {
  id: string,
  prefix?: string,
  asn?: string,
  comment?: string,
  maximumLength: number,
  validated : IBgp[],
  invalidated : IBgp[]
}

export interface IWhitelistResponse {
  links: {
    first: string,
    prev: string,
    next: string,
    last: string
  };
  data: IWhitelistEntry[];
  metadata: {
    totalCount: number
  };
}
