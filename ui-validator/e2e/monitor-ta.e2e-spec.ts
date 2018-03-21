import { AppPage } from './app.po';
import {MonitorTaPage} from "./pages/MonitorTaPage";

describe('Monitor TA Page', () => {
  let monitorTaPage: MonitorTaPage;

  beforeEach(() => {
    const page = new AppPage();
    monitorTaPage = page.navigateToMonitorTa();
  });

  it('should display correctly Monitor TA page', () => {
    monitorTaPage
      .expectTitleHeaderToBe('Overview for RIPE NCC RPKI Root');
  });
});
