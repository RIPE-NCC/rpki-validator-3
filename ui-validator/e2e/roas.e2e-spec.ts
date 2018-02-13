import { AppPage } from './app.po';

describe('ui-validator App', () => {
  let page: AppPage;

  beforeEach(() => {
    page = new AppPage();
  });

  it('should display Validated ROAs page title', () => {
    page.navigateTo("roas");
    expect(page.getParagraphText()).toEqual('Validated ROAs');
  });
});
