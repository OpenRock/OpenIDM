// module.exports = {
//   'Add XML Connector': function (client) {
//     client.globals.login.helpers.login(client);
//
//     client
//     .waitForElementPresent("#AddConnector", 2000)
//     .click("#AddConnector")
//     .waitForElementPresent("#connectorName", 2000);
//     .setValue("#connectorName","XML")
//     .setValue("#connectorType","org.forgerock.openicf.connectors.xml.XMLConnector_1.1.0.2")
//     .setValue("#xmlxsdicfFilePath", path.join(basePath, xml.XSD_ICF_File_Path))
//     .setValue("#xmlxsdFilePath", path.join(basePath, xml.XSD_File_Path))
//     .setValue("#xmlFilePath", path.join(basePath, xml.XML_File_Path))
//     .click("#submitConnector")
//
//     .url('http://localhost:8080/admin/#mapping/add/connector/xml')
//     .click("#mappingSaveOkay")
//
//     client.end();
//   }
// };
