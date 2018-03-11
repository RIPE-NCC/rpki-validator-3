export interface IAnnouncement {
  origin: string,
  prefix: string,
  validity: string,
  maxLength: number,
  ta: string,
  uri: string
}

export interface IAnnouncementData {
  origin: string,
  prefix: string,
  validity: string,
  validatingRoas: Array<IAnnouncement>
}

export interface IAnnouncementResponse {
  data: IAnnouncementData,
  metadata: {
    totalCount: number
  }
}
