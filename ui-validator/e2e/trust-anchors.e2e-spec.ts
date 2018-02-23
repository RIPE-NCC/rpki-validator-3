import { AppPage } from './app.po';

describe('ui-validator App', () => {
  let page: AppPage;

  beforeEach(() => {
    page = new AppPage();
  });

  it('should display Configured Trust Anchors page title', () => {
    page.navigateTo('trust-anchors');
    expect(page.getParagraphText()).toEqual('Configured Trust Anchors');
  });
});
