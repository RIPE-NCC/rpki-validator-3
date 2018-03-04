export interface IIgnoreFilter {
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
    ignoreFilters: IIgnoreFilter[]
  },
  metadata: {
    totalCount: number
  }
}
