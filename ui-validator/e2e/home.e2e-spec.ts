import {AppPage} from './app.po';

describe('ui-validator App', () => {
  let page: AppPage;

  beforeEach(() => {
    page = new AppPage();
  });

  it('should display Quick Overview of BGP Origin Validation page title', () => {
    page.navigateTo('home');
    expect(page.getParagraphText()).toEqual('Quick Overview of BGP Origin Validation');
  });

  it('should show Trust Anchors quick overview text in panel', () => {
    expect(page.getTrustAnchorsTabElement().getText()).toEqual('Trust Anchors');
    page.getTrustAnchorsTabElement().click();
    expect(page.getQuickOverviewPanel().isPresent()).toEqual(true);
    const tsOverview = 'Trust anchors are the entry points used for validation in any Public Key Infrastructure (PKI) system.\n' +
      'This RPKI Validator is preconfigured with the trust anchors for AFRINIC, APNIC, Lacnic and RIPE NCC. In order to obtain the trust anchor for the ARIN RPKI repository, you will first have to accept their Relying Party Agreement. Please refer to the README.txt for details on how to add trust anchors to this application.';
    expect(page.getQuickOverviewPanel().getText()).toEqual(tsOverview);
  });

  it('should show ROAs quick overview text in panel', () => {
    expect(page.getRoasTabElement().getText()).toEqual('ROAs');
    page.getTrustAnchorsTabElement().click();

  });
});
