package org.traccar.protocol;

import org.junit.jupiter.api.Test;
import org.traccar.ProtocolTest;
import org.traccar.model.Position;

public class VortexProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecode() throws Exception {

        var decoder = inject(new VortexProtocolDecoder(null));

        verifyDecode(decoder, text(
                "LICORNE.VORTEX.{\"IMEI\":\"012345678912345\",\"CCID\":\"9999999999999999999\",",
                "\"Firmware\":\"1.0.0\",\"Config\":\"1.1.0\",\"CarModel\":\"Renault-Clio-2019\",",
                "\"CarModelVersion\":\"1.2\",\"VIN\":\"V1234567AKD12345\"}"),
                position(Checks.ATTRIBUTES));

        verifyDecode(decoder, text(
                "$2024,10,14,06:10:53,36.749912,2.998375,34,108,128,207809,1341,",
                "108457,29,72,1,460000151ED9001,533,1047,12,"),
                position());

        verifyDecode(decoder, text(
                "$2024,10,14,06:10:53,36.749912,2.998375,34,108,129,207809,1341,",
                "108457,29,72,4,460000151ED9001,,,"),
                position().attribute(Position.KEY_IGNITION, true));

        verifyDecode(decoder, text(
                "$2024,10,14,06:10:53,36.749912,2.998375,34,108,128,207809,1341,",
                "108457,29,72,16,460000151ED9001"),
                position().attribute(Position.KEY_ALARM, Position.ALARM_TOW));

    }

}
