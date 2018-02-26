export interface IBgp {
  asn: string,
  prefix: string,
  validity: string
}

export interface IBgpResponse {
  links: {},
  data:  Array<IBgp>,
  metadata: {
    totalCount: number
  }
}
