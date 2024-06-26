package com.alibaba.polardbx.qatest.ddl.auto.partition;

import org.junit.Test;
import org.junit.runners.Parameterized;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * @author chenghui.lch
 */

public class PartitionColumnTypeForKey5Test extends PartitionColumnTypePrepStmtTestBase {

    public TestParameter parameter;

    public PartitionColumnTypeForKey5Test(TestParameter parameter) {
        super(parameter);
        this.testDbName = this.testDbName + "_k5";
        this.testQueryByPrepStmt = false;
        this.testInsertByPrepStmt = false;
    }

    @Parameterized.Parameters(name = "{index}: partColTypeTestCase {0}")
    public static List<TestParameter> parameters() {
        // partition strategy: range/list/hash/range column/list column/key
        // data type: numeric/string/time
        return Arrays.asList(

            /**
             * ========= Key, char/int -> decimal(24,0)  ===========
             * === -999999999999999999999999 ~ 999999999999999999999999  ===
             */
            new TestParameter(
                "key_decimal_2400",
                new String[] {"c1"}/*col*/,
                new String[] {"decimal(24,0) default null "}/*data types*/,
                new String[] {"set sql_mode='';set names utf8;", "", ""}/*prepStmts*/,
                "key"/*strategy*/,
                new String[] {"16"}/*bndVal*/,
                new String[] {
                    "(0)",
                    "(null)",
                    "(-1234567890123456789012340)",
                    "(-999999999999999999999999)",
                    "(-123456789012345678901234)",
                    "(-123456789012345678901234)",
                    "(-9223372036854775808)",
                    "(-9223372036854775808)",
                    "(-9223372036854775807)",
                    "(-9223372036854775808)",
                    "(-9223372036854775803)",
                    "(-2147483653)",
                    "(-2147483648)",
                    "(-2147483643)",
                    "(-8388613)",
                    "(-8388608)",
                    "(-8388603)",
                    "(-32773)",
                    "(-32768)",
                    "(-32763)",
                    "(-133)",
                    "(-128)",
                    "(-123)",
                    "(-5)",
                    "(0)",
                    "(5)",
                    "(122)",
                    "(127)",
                    "(132)",
                    "(255)",
                    "(32762)",
                    "(32767)",
                    "(32772)",
                    "(65530)",
                    "(65535)",
                    "(65540)",
                    "(8388602)",
                    "(8388607)",
                    "(8388612)",
                    "(16777210)",
                    "(16777215)",
                    "(16777220)",
                    "(2147483642)",
                    "(2147483647)",
                    "(2147483652)",
                    "(4294967290)",
                    "(4294967295)",
                    "(4294967300)",
                    "(9223372036854775802)",
                    "(9223372036854775807)",
                    "(9223372036854775812)",
                    "(18446744073709551610)",
                    "(18446744073709551615)",
                    "(123456789012345678901234)",
                    "(999999999999999999999999)",
                    "(1234567890123456789012340)",
                }/*insertValues*/
                ,
                new String[] {
                    "('-1234567890123456789012340')",
                    "('-999999999999999999999999')",
                    "('-123456789012345678901234')",
                    "('-123456789012345678901234')",
                    "('-9223372036854775808')",
                    "('-9223372036854775807')",
                    "('-922337203685477580')",
                    "('-922337203685477579')",
                    "('-9223372036854775803')",
                    "('-2147483653')",
                    "('-2147483648')",
                    "('-2147483643')",
                    "('-8388613')",
                    "('-8388608')",
                    "('-8388603')",
                    "('-32773')",
                    "('-32768')",
                    "('-32763')",
                    "('-133')",
                    "('-128')",
                    "('-123')",
                    "('-5')",
                    "('0')",
                    "('5')",
                    "('122')",
                    "('127')",
                    "('132')",
                    "('255')",
                    "('32762')",
                    "('32767')",
                    "('32772')",
                    "('65530')",
                    "('65535')",
                    "('65540')",
                    "('8388602')",
                    "('8388607')",
                    "('8388612')",
                    "('16777210')",
                    "('16777215')",
                    "('16777220')",
                    "('2147483642')",
                    "('2147483647')",
                    "('2147483652')",
                    "('4294967290')",
                    "('4294967295')",
                    "('4294967300')",
                    "('9223372036854775802')",
                    "('9223372036854775807')",
                    "('9223372036854775812')",
                    "('18446744073709551610')",
                    "('18446744073709551615')",
                    "('123456789012345678901234')",
                    "('999999999999999999999999')",
                    "('1234567890123456789012340')"
                }/*selectValues*/
                ,
                new String[] {
                    "('-9223372036854775808')",
                    "('-9223372036854775807')",
                    "('-9223372036854775803')",
                    "('-2147483653')",
                    "('-2147483648')",
                    "('-2147483643')",
                    "('-8388613')",
                    "('-8388608')",
                    "('-8388603')",
                    "('-32773')",
                    "('-32768')",
                    "('-32763')",
                    "('-133')",
                    "('-128')",
                    "('-123')",
                    "('-5')",
                    "('0')",
                    "('5')",
                    "('122')",
                    "('127')",
                    "('132')",
                    "('255')",
                    "('32762')",
                    "('32767')",
                    "('32772')",
                    "('65530')",
                    "('65535')",
                    "('65540')",
                    "('8388602')",
                    "('8388607')",
                    "('8388612')",
                    "('16777210')",
                    "('16777215')",
                    "('16777220')",
                    "('2147483642')",
                    "('2147483647')",
                    "('2147483652')",
                    "('4294967290')",
                    "('4294967295')",
                    "('4294967300')",
                    "('9223372036854775802')",
                    "('9223372036854775807')",
                    "('9223372036854775812')",
                    "('18446744073709551610')",
                    "('18446744073709551615')",
                    "('18446744073709551616')",
                    "('123456789012345678901234')",
                    "('999999999999999999999999')",
                    "('1234567890123456789012340')"
                }/*rngSortValues*/
            ),

            /**
             * ========= Key, char/int -> decimal(65,0)  ===========
             * ===
             * -99999999999999999999999999999999999999999999999999999999999999999
             * ~
             *  99999999999999999999999999999999999999999999999999999999999999999
             * ===
             */
            new TestParameter(
                "key_decimal_6500",
                new String[] {"c1"}/*col*/,
                new String[] {"decimal(65,0) default null "}/*data types*/,
                new String[] {"set sql_mode='';set names utf8;", "", ""}/*prepStmts*/,
                "key"/*strategy*/,
                new String[] {"16"}/*bndVal*/,
                new String[] {
                    "(0)",
                    "(null)",
                    "(-1234567890123456789012340)",
                    "(-999999999999999999999999)",
                    "(-123456789012345678901234)",
                    "(-123456789012345678901234)",
                    "(-9223372036854775808)",
                    "(-9223372036854775808)",
                    "(-9223372036854775807)",
                    "(-9223372036854775808)",
                    "(-9223372036854775803)",
                    "(-2147483653)",
                    "(-2147483648)",
                    "(-2147483643)",
                    "(-8388613)",
                    "(-8388608)",
                    "(-8388603)",
                    "(-32773)",
                    "(-32768)",
                    "(-32763)",
                    "(-133)",
                    "(-128)",
                    "(-123)",
                    "(-5)",
                    "(0)",
                    "(5)",
                    "(122)",
                    "(127)",
                    "(132)",
                    "(255)",
                    "(32762)",
                    "(32767)",
                    "(32772)",
                    "(65530)",
                    "(65535)",
                    "(65540)",
                    "(8388602)",
                    "(8388607)",
                    "(8388612)",
                    "(16777210)",
                    "(16777215)",
                    "(16777220)",
                    "(2147483642)",
                    "(2147483647)",
                    "(2147483652)",
                    "(4294967290)",
                    "(4294967295)",
                    "(4294967300)",
                    "(9223372036854775802)",
                    "(9223372036854775807)",
                    "(9223372036854775812)",
                    "(18446744073709551610)",
                    "(18446744073709551615)",
                    "(123456789012345678901234)",
                    "(999999999999999999999999)",
                    "(1234567890123456789012340)",
                }/*insertValues*/
                ,
                new String[] {
                    "('-1234567890123456789012340')",
                    "('-999999999999999999999999')",
                    "('-123456789012345678901234')",
                    "('-123456789012345678901234')",
                    "('-9223372036854775808')",
                    "('-9223372036854775807')",
                    "('-922337203685477580')",
                    "('-922337203685477579')",
                    "('-9223372036854775803')",
                    "('-2147483653')",
                    "('-2147483648')",
                    "('-2147483643')",
                    "('-8388613')",
                    "('-8388608')",
                    "('-8388603')",
                    "('-32773')",
                    "('-32768')",
                    "('-32763')",
                    "('-133')",
                    "('-128')",
                    "('-123')",
                    "('-5')",
                    "('0')",
                    "('5')",
                    "('122')",
                    "('127')",
                    "('132')",
                    "('255')",
                    "('32762')",
                    "('32767')",
                    "('32772')",
                    "('65530')",
                    "('65535')",
                    "('65540')",
                    "('8388602')",
                    "('8388607')",
                    "('8388612')",
                    "('16777210')",
                    "('16777215')",
                    "('16777220')",
                    "('2147483642')",
                    "('2147483647')",
                    "('2147483652')",
                    "('4294967290')",
                    "('4294967295')",
                    "('4294967300')",
                    "('9223372036854775802')",
                    "('9223372036854775807')",
                    "('9223372036854775812')",
                    "('18446744073709551610')",
                    "('18446744073709551615')",
                    "('123456789012345678901234')",
                    "('999999999999999999999999')",
                    "('1234567890123456789012340')"
                }/*selectValues*/
                ,
                new String[] {
                    "('-9223372036854775808')",
                    "('-9223372036854775807')",
                    "('-9223372036854775803')",
                    "('-2147483653')",
                    "('-2147483648')",
                    "('-2147483643')",
                    "('-8388613')",
                    "('-8388608')",
                    "('-8388603')",
                    "('-32773')",
                    "('-32768')",
                    "('-32763')",
                    "('-133')",
                    "('-128')",
                    "('-123')",
                    "('-5')",
                    "('0')",
                    "('5')",
                    "('122')",
                    "('127')",
                    "('132')",
                    "('255')",
                    "('32762')",
                    "('32767')",
                    "('32772')",
                    "('65530')",
                    "('65535')",
                    "('65540')",
                    "('8388602')",
                    "('8388607')",
                    "('8388612')",
                    "('16777210')",
                    "('16777215')",
                    "('16777220')",
                    "('2147483642')",
                    "('2147483647')",
                    "('2147483652')",
                    "('4294967290')",
                    "('4294967295')",
                    "('4294967300')",
                    "('9223372036854775802')",
                    "('9223372036854775807')",
                    "('9223372036854775812')",
                    "('18446744073709551610')",
                    "('18446744073709551615')",
                    "('18446744073709551616')",
                    "('123456789012345678901234')",
                    "('999999999999999999999999')",
                    "('1234567890123456789012340')"
                }/*rngSortValues*/
            ),

            /**
             * ========= Key, char/int -> decimal(65,0)  ===========
             * ===
             * -99999999999999999999999999999999999999999999999999999999999999999
             * ~
             *  99999999999999999999999999999999999999999999999999999999999999999
             * ===
             */
            new TestParameter(
                "key_decimal_6530",
                new String[] {"c1"}/*col*/,
                new String[] {"decimal(65,30) default null "}/*data types*/,
                new String[] {"set sql_mode='';set names utf8;", "", ""}/*prepStmts*/,
                "key"/*strategy*/,
                new String[] {"16"}/*bndVal*/,
                new String[] {
                    "(0)",
                    "(null)",
                    "('-9223372036854775808.75808')",
                    "('-9223372036854775807.75807')",
                    "('-9223372036854775803.75803')",
                    "('-2147483653.83653')",
                    "('-2147483648.83648')",
                    "('-2147483643.83643')",
                    "('-8388613.8613')",
                    "('-8388608.8608')",
                    "('-8388603.8603')",
                    "('-32773.2773')",
                    "('-32768.2768')",
                    "('-32763')",
                    "('-133')",
                    "('-128')",
                    "('-123')",
                    "('-5')",
                    "('0')",
                    "('5')",
                    "('122')",
                    "('127')",
                    "('132')",
                    "('255')",
                    "('32762.2762')",
                    "('32762.2762')",
                    "('32772.2772')",
                    "('65530.5530')",
                    "('65535.5535')",
                    "('65540.5540')",
                    "('8388602.88602')",
                    "('8388607.88607')",
                    "('8388612.88612')",
                    "('16777210.77210')",
                    "('16777215.77215')",
                    "('16777220.77220')",
                    "('2147483642.83642')",
                    "('2147483647.83647')",
                    "('2147483652.83652')",
                    "('4294967290.67290')",
                    "('4294967295.67295')",
                    "('4294967300.67300')",
                    "('9223372036854775802.75802')",
                    "('9223372036854775807.75807')",
                    "('9223372036854775812.75812')",
                    "('18446744073709551610.51610')",
                    "('18446744073709551615.51615')",
                    "('18446744073709551616.51616')",
                    "('123456789012345678901234.01234')",
                    "('999999999999999999999999.99999')",
                    "('1234567890123456789012340.12340')"
                }/*insertValues*/
                ,
                new String[] {
                    "(0)",
                    "(null)",
                    "('-9223372036854775808.75808')",
                    "('-9223372036854775807.75807')",
                    "('-9223372036854775803.75803')",
                    "('-2147483653.83653')",
                    "('-2147483648.83648')",
                    "('-2147483643.83643')",
                    "('-8388613.8613')",
                    "('-8388608.8608')",
                    "('-8388603.8603')",
                    "('-32773.2773')",
                    "('-32768.2768')",
                    "('-32763')",
                    "('-133')",
                    "('-128')",
                    "('-123')",
                    "('-5')",
                    "('0')",
                    "('5')",
                    "('122')",
                    "('127')",
                    "('132')",
                    "('255')",
                    "('32762.2762')",
                    "('32762.2762')",
                    "('32772.2772')",
                    "('65530.5530')",
                    "('65535.5535')",
                    "('65540.5540')",
                    "('8388602.88602')",
                    "('8388607.88607')",
                    "('8388612.88612')",
                    "('16777210.77210')",
                    "('16777215.77215')",
                    "('16777220.77220')",
                    "('2147483642.83642')",
                    "('2147483647.83647')",
                    "('2147483652.83652')",
                    "('4294967290.67290')",
                    "('4294967295.67295')",
                    "('4294967300.67300')",
                    "('9223372036854775802.75802')",
                    "('9223372036854775807.75807')",
                    "('9223372036854775812.75812')",
                    "('18446744073709551610.51610')",
                    "('18446744073709551615.51615')",
                    "('18446744073709551616.51616')",
                    "('123456789012345678901234.01234')",
                    "('999999999999999999999999.99999')",
                    "('1234567890123456789012340.12340')"
                }/*selectValues*/
                ,
                new String[] {
                    "('-9223372036854775808.75808')",
                    "('-9223372036854775807.75807')",
                    "('-9223372036854775803.75803')",
                    "('-2147483653.83653')",
                    "('-2147483648.83648')",
                    "('-2147483643.83643')",
                    "('-8388613.8613')",
                    "('-8388608.8608')",
                    "('-8388603.8603')",
                    "('-32773.2773')",
                    "('-32768.2768')",
                    "('-32763')",
                    "('-133')",
                    "('-128')",
                    "('-123')",
                    "('-5')",
                    "('0')",
                    "('5')",
                    "('122')",
                    "('127')",
                    "('132')",
                    "('255')",
                    "('32762.2762')",
                    "('32762.2762')",
                    "('32772.2772')",
                    "('65530.5530')",
                    "('65535.5535')",
                    "('65540.5540')",
                    "('8388602.88602')",
                    "('8388607.88607')",
                    "('8388612.88612')",
                    "('16777210.77210')",
                    "('16777215.77215')",
                    "('16777220.77220')",
                    "('2147483642.83642')",
                    "('2147483647.83647')",
                    "('2147483652.83652')",
                    "('4294967290.67290')",
                    "('4294967295.67295')",
                    "('4294967300.67300')",
                    "('9223372036854775802.75802')",
                    "('9223372036854775807.75807')",
                    "('9223372036854775812.75812')",
                    "('18446744073709551610.51610')",
                    "('18446744073709551615.51615')",
                    "('18446744073709551616.51616')",
                    "('123456789012345678901234.01234')",
                    "('999999999999999999999999.99999')",
                    "('1234567890123456789012340.12340')"
                }/*rngSortValues*/
            )
        );

    }

    @Test
    public void runTest() throws SQLException {
        super.testInsertAndSelect();
    }

}
