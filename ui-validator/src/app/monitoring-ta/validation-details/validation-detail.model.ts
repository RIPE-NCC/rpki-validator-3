export interface IValidationCheck {
  location: string,
  status: boolean,
  key: string,
  parameters: string[],
  formattedMessage: string
}

export interface IValidationChecksResponse {
  links: {
    first: string,
    prev: string,
    next: string,
    last: string
  },
  data: IValidationCheck[],
  metadata: {
    totalCount: number
  }
}

