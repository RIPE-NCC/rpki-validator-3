export interface IIgnoreFilter {
  id: string,
  prefix?: string,
  asn?: string,
  comment?: string
}

export interface IIgnoreFiltersResponse {
  links: {
    first: string,
    prev: string,
    next: string,
    last: string
  },
  data: IIgnoreFilter[],
  metadata: {
    totalCount: number
  }
}
