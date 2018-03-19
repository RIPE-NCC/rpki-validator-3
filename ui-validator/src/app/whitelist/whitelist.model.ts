export interface IWhitelistEntry {
  id: string,
  prefix?: string,
  asn?: string,
  comment?: string,
  maximumLength: number
}

export interface IWhitelistResponse {
  links: {
    first: string,
    prev: string,
    next: string,
    last: string
  },
  data: IWhitelistEntry[],
  metadata: {
    totalCount: number
  }
}
