import {by, element} from 'protractor';
import {RoasPage} from './pages/RoasPage';
import {TrustAnchorsPage} from './pages/TrustAnchorsPage';
import {MonitorTaPage} from './pages/MonitorTaPage';
import {IgnoreFiltersPage} from './pages/IgnoreFiltersPage';
import {AnnouncementPreviewPage} from './pages/AnnouncementPreviewPage';
import {WhitelistPage} from './pages/WhitelistPage';
import {BgpPage} from "./pages/BgpPage";

export class AppPage {

  navigateToRoas(q: string) {
    return new RoasPage(q);
  }

  navigateToTrustAnchors() {
    return new TrustAnchorsPage();
  }

  navigateToMonitorTa() {
    return new MonitorTaPage();
  }

  navigateToBgp(q:string) {
    return new BgpPage(q);
  }

  navigateToIgnoreFilters() {
    return new IgnoreFiltersPage();
  }

  navigateToWhitelist() {
    return new WhitelistPage();
  }

  navigateToAnnouncementPreview(asn: string, prefix: string) {
    return new AnnouncementPreviewPage(asn, prefix);
  }

  getParagraphText() {
    return element(by.css('app-root h1')).getText();
  }

  getElementByCss(byCss: string) {
    return element(by.css(byCss));
  }
}
