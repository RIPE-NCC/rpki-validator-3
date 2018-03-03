export interface IIgnoreFilters {
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
  data: {
    ignoreFilters: IIgnoreFilters[]
  },
  metadata: {
    totalCount: number
  }
}
