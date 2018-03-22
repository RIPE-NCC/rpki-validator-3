export interface IResponse {
  links: {
    first: string,
    prev: string,
    next: string,
    last: string
  },
  data:  any[],
  metadata: {
    totalCount: number
  }
}
