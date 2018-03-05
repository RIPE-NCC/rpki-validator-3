export interface IBgp {
  asn: string,
  prefix: string,
  validity: string
}

export interface IBgpResponse {
  data:  Array<IBgp>,
  metadata: {
    totalCount: number
  }
}
