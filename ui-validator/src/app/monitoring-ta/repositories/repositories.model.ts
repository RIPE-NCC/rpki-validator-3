export interface IRepository {
  id: string;
  locationURI: string;
  type: string;
  status: string;
  lastDownloadedAt: string;
}

export interface IRepositoriesStatuses {
  downloaded: number;
  pending: number;
  failed: number;
}
