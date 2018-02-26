import { browser, by, element } from 'protractor';
import {RoasPage} from "./pages/RoasPage";
import {TrustAnchorsPage} from "./pages/TrustAnchorsPage";

export class AppPage {
  navigateTo() {
    return browser.get('/');
  }

  navigateTo(page: string) {
    return browser.get('/' + page);
  }

  navigateToRoas() {
    return new RoasPage();
  }

  navigateToTrustAnchors() {
    return new TrustAnchorsPage();
  }

  getParagraphText() {
    return element(by.css('app-root h1')).getText();
  }

  getElementByCss(byCss: string) {
    return element(by.css(byCss));
  }
}
