import {AppPage} from './app.po';
import {WhitelistPage} from "./pages/WhitelistPage";

describe('Whitelist Page', () => {
  let whitelistPage: WhitelistPage;

  beforeEach(() => {
    const page = new AppPage();
    whitelistPage = page.navigateToWhitelist();
  });

  describe('add entry to whitelist', () => {

    it('should display correctly Whitelist title', () => {
      whitelistPage
        .expectTitleHeaderToBe('Whitelist');
    });

    it('should show validation error if prefix and asn are empty', () => {
      whitelistPage
        .clearEntryForm()
        .expectPrefixAndAsnValidationMessage();
    });

    it('should not add a entry to whitelist with invalid prefix', () => {
      whitelistPage
        .addWhitelistEntry('NotValid', 'AS1234')
        .expectPrefixValidationMessage()
    });

    it('should not add a entry to whitelist with invalid asn', () => {
      whitelistPage
        .addWhitelistEntry('1.0.128.0/20', 'NotValid')
        .expectAsnValidationMessage()
    });

    it('should add a entry without and with comment', () => {
      whitelistPage
        .addWhitelistEntry('1.0.128.0/20', 'AS1234')
        .expectEntryAdded()
        .clearEntryForm()
        .addWhitelistEntry('1.0.128.0/20', 'AS1234', 13)
        .expectEntryAdded()
        .clearEntryForm()
        .addWhitelistEntry('1.0.128.0/20', 'AS1234', 12,'e2e test comment')
        .expectEntryAdded()
    });

    it('should not add a entry with maxLength < 12 and > 32', () => {
      whitelistPage
        .addWhitelistEntry('1.0.128.0/20', 'AS1234', 11, 'comment')
        .expectMaxLengthValidationMessage()
        .clearEntryForm()
        .addWhitelistEntry('1.0.128.0/20', 'AS1234', 33, 'comment')
        .expectMaxLengthValidationMessage()
        .clearEntryForm()
        .addWhitelistEntry('1.0.128.0/20', 'AS1234', 32, 'comment')
    });
  });

  describe('current whitelist entry', () => {
    it('should show entry on table', () => {
      whitelistPage
        .expectNumberOfTableRowsToBe(2)
        .expectRowColumnToBe(0, 'Prefix', '1.0.128.0/20')
        .expectRowColumnToBe(0, 'ASN', '1234')
        .expectRowColumnToBe(1, 'Prefix', '2.0.128.0/20')
        .expectRowColumnToBe(1, 'ASN', '3215')
        .expectRowColumnToBe(1, 'Maximum prefix length', '20')
    });

    it('should delete entry on table', () => {
      whitelistPage
        .expectNumberOfTableRowsToBe(2)
        .deleteEntry(1)
    });

    it('should show popover on mouse over announcement > 0', () => {
      whitelistPage
        .expectNumberOfAnnouncments(0, 3, '0 announcement(s)')
        .expectPopoverToHave(0, 3, false)
        .expectNumberOfAnnouncments(0, 4, '1 announcement(s)')
        .expectPopoverToHave(0, 4, true)
        .moveMouseOverColumn(0, 4)
        .expectPopoverOnHover(0, 4)
        .moveMouseOverRowInPopover(0, 4)
        .expectTooltipTextToBe(0, 4, 'Click link to see details')
        .expectNumberOfAnnouncments(1, 3, '1 announcement(s)')
        .expectPopoverToHave(1, 3, true)
        .expectNumberOfAnnouncments(1, 4, '0 announcement(s)')
        .expectPopoverToHave(1, 4, false)
        .moveMouseOverColumn(1, 3)
        .expectPopoverOnHover(1, 3)
        .moveMouseOverRowInPopover(1, 3)
        .expectTooltipTextToBe(1, 3, 'Click link to see details');
    });

    it('should navigate to selected announcement on click in popover', () => {
      whitelistPage
        .moveMouseOverColumn(0, 4)
        .expectPopoverOnHover(0, 4)
        .clickOnRowInPopoverOfSpecifiedCell(0, 4)
        .expectToOpenAnnouncementPage('AS56203', '1.0.6.0/24');
    });
  });
});
