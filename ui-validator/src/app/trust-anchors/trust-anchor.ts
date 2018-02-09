export interface ITrustAnchorOverview {
  taName: string;
  successful: number;
  warnings: string;
  errors: string[];
  lastUpdated: string[];
}

export interface ITrustAnchorOverviewResponse {
    data: ITrustAnchorOverview[];
}

export interface ITrustAnchor {
  type: string;
  id: number;
  name: string;
  locations: string[];
  subjectPublicKeyInfo: string;
  preconfigured: boolean;
  initialCertificateTreeValidationRunCompleted: boolean,
  certificate: string;
  links: {
    self: string;
  }
}

export interface ITrustAnchorResponse {
  links : {
    self: string;
  }
  data: ITrustAnchor[];
}
