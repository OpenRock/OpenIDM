var path = require('path');
var xml = {
  XSD_ICF_File_Path :"openidm/openidm-zip/target/openidm/samples/sample1/data/resource-schema-1.xsd",
  XSD_File_Path :"openidm/openidm-zip/target/openidm/samples/sample1/data/resource-schema-extension.xsd",
  XML_File_Path :"openidm/openidm-zip/target/openidm/samples/sample1/data/xmlConnectorData.xml",
  basePath: "/Users/javier/ForgeRock/",
  value: "org.forgerock.openicf.connectors.xml.XMLConnector_1.1.0.2"
};

module.exports = {
  'Add XML Connector': function (client) {
    client.globals.login.helpers.login(client);

    client
      .waitForElementPresent("#AddConnector", 1000)
      .click("#AddConnector")
      .waitForElementPresent("#connectorName", 1000)
      .setValue("#connectorName","XML")
      .click('#connectorType')
      .waitForElementVisible('option[value="'+ xml.value +'"]', 1000)
      .click('option[value="'+ xml.value +'"]')
      .setValue("#xmlxsdicfFilePath", path.join(xml.basePath, xml.XSD_ICF_File_Path))
      .setValue("#xmlxsdFilePath", path.join(xml.basePath, xml.XSD_File_Path))
      .setValue("#xmlFilePath", path.join(xml.basePath, xml.XML_File_Path))
      .click("#submitConnector")
      .url('http://localhost:8080/admin/#mapping/add/connector/xml')
      .waitForElementVisible('[data-connector-title="xml"]', 1000)
      .click('[data-connector-title="xml"]')
      .click('[data-managed-title="user"]')
      .click("#createMapping")
      .waitForElementVisible("#mappingSaveOkay", 1000)
      .click("#mappingSaveOkay")
      .waitForElementVisible("#behaviorsTab", 750)
      .click("#behaviorsTab")
      .waitForElementVisible("#policyPatterns", 750)
      .click("#policyPatterns")
      .click('option[value="Default Actions"]')
      .click('input[value="Save"]')
      .click("#propertiesTab")
    // client.end();
  }
};
