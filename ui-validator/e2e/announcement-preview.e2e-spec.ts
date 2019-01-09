import {AppPage} from './app.po';

import {AnnouncementPreviewPage} from "./pages/AnnouncementPreviewPage";

describe('Announcement Preview Page', () => {
  let page;
  let announcementPreviewPage: AnnouncementPreviewPage;

  beforeEach(() => {
    page = new AppPage();
  });

  describe('navigate from bgp table', () => {
    beforeEach(() => {
      announcementPreviewPage = page.navigateToAnnouncementPreview('AS3215', '2.0.0.0/16');
    });

    it('should display correctly Announcement Preview page', () => {
      announcementPreviewPage
        .expectTitleHeaderToBe('Announcement Preview')
        .expectASNInPanelToBe('AS3215')
        .expectPrefixInPanelToBe('2.0.0.0/16')
        .expectGreenFlagInPanel('VALID')
        .expectSubtitleToBe('Relevant Validated ROAs')
        .expectNumberOfTableRowsToBe(3);
    });
  });

  describe('navigate from menu', () => {

    beforeEach(() => {
      announcementPreviewPage = page.navigateToAnnouncementPreview();
    });

    it('should display correctly modal window for Announcement', () => {
      announcementPreviewPage
        .expectShowModalWindow()
        .expectModalWindowMessage('Please enter both an ASN and a Prefix')
        .expectModalWindowToContainInputField('prefix')
        .expectModalWindowToContainInputField('asn')
        .expectShowButtonOnModalToBe(false);
    });

    it('should disable Show btn until asn and prefix are not specified', () => {
      announcementPreviewPage
        .setValueToInput('prefix', '2')
        .expectShowButtonOnModalToBe(false)
        .setValueToInput('asn', 'AS1234')
        .expectShowButtonOnModalToBe(true)
        .removeValueFromInput('prefix')
        .expectShowButtonOnModalToBe(false);
    });

    it('should show Announcement for specifed prefix and asn in modal window', () => {
      announcementPreviewPage
        .setValueToInput('prefix', '2.0.0.0/16')
        .setValueToInput('asn', 'AS3215')
        .expectShowButtonOnModalToBe(true)
        .goToAnnouncementPage()
        .expectTitleHeaderToBe('Announcement Preview')
        .expectASNInPanelToBe('AS3215')
        .expectPrefixInPanelToBe('2.0.0.0/16')
        .expectGreenFlagInPanel('VALID')
        .expectSubtitleToBe('Relevant Validated ROAs')
        .expectNumberOfTableRowsToBe(3)
        .expectFlagFiltered(3, 'FILTERED VALID')
    });

    it('should navigate to bgp page on dismiss modal window', () => {
      announcementPreviewPage
        .expectShowModalWindow()
        .dismissModalWindow()
        .expectToOpenBgpPage();
    });
  })
});
