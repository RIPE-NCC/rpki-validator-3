import { AppPage } from './app.po';
import {MonitorTaPage} from "./pages/MonitorTaPage";

describe('ui-validator App', () => {
  let monitorTaPage: MonitorTaPage;

  beforeEach(() => {
    const page = new AppPage();
    monitorTaPage = page.navigateToMonitorTa();
  });

  it('should display correctly Monitor TA page', () => {
    monitorTaPage
      .expectTitleHeaderToBe('Monitor for RIPE NCC RPKI Root');
  });
});
