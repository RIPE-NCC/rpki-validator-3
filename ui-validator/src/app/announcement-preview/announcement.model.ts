export interface IAnnouncement {
  origin: string,
  prefix: string,
  validity: string,
  maxLength: number,
  ta: string,
  uri: string
}

export interface IAnnouncementResponse {
  data:  Array<IAnnouncement>,
  metadata: {
    totalCount: number
  }
}

