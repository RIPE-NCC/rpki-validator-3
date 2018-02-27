export interface IRepository {
  url: string,
  type: string,
  status: string,
  lastChecked: string
}

export interface IRepositoriesStatuses {
  valid: number,
  warning: number,
  invalid: number,
}
