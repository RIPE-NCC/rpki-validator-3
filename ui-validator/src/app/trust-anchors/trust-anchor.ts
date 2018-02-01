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
    validationChecks : {
        success: number,
        warning: number,
        danger: number
    }
}