export interface IRoa {
  asn: string,
  prefix: string,
  length: number,
  trustAnchor: string,
  uri: string
}

export interface IRoasResponse {
  links: {
    first: string,
    prev: string,
    next: string,
    last: string
  },
  data: IRoa[],
  metadata: {
    totalCount: number
  }
}
