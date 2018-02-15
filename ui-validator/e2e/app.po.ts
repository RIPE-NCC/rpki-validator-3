import { browser, by, element } from 'protractor';

export class AppPage {
  navigateTo() {
    return browser.get('/');
  }

  navigateTo(page: string) {
    return browser.get('/' + page);
  }

  getParagraphText() {
    return element(by.css('app-root h1')).getText();
  }

  getElementByCss(byCss: string) {
    return element(by.css(byCss));
  }

  getQuickOverviewPanel() {
    return element(by.css('#ngb-tab-0-panel')).getText();
  }

  getTrustAnchorsTabElement() {
    return element(by.css('#ngb-tab-0'));
  }

  getRoasTabElement() {
    return element(by.css('#ngb-tab-1'));
  }
}
