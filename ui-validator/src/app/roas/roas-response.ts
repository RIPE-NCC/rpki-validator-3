export interface IRoa {
  asn: string,
  prefix: string,
  length: number,
  trustAnchor: string,
  link: string
}

export interface IRoasResponse {
  links: {
    first: string,
    prev: string,
    next: string,
    last: string
  },
  data: IRoa[]
}
