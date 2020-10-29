export interface ITrustAnchorOverview {
  id: string;
  taName: string;
  successful: number;
  warnings: string;
  errors: string[];
  lastUpdated: string[];
  earliestObjectExpiration: string[];
  completedValidation: boolean;
}

export interface ITrustAnchor {
  type: string;
  id: number;
  name: string;
  locations: string[];
  subjectPublicKeyInfo: string;
  rsyncPrefetchUri?: string,
  preconfigured: boolean;
  initialCertificateTreeValidationRunCompleted: boolean,
  certificate: string;
  links: {
    self: string;
  };
}

