package com.aircas.ptr.foundry.sync.pg;


/**
 * PostgreSQL的事件日志解析
 * 
 * @author yibo.tang
 * @date 2021-04-27 11:07:00
 * @since 1.0
 *
 */
public class EventParser {

    private static boolean isBegin(String msg) {
        return msg != null
                && msg.length() > 5
                && (msg.charAt(0) == 'B' || msg.charAt(0) == 'b')
                && (msg.charAt(1) == 'E' || msg.charAt(1) == 'e')
                && (msg.charAt(2) == 'G' || msg.charAt(2) == 'g')
                && (msg.charAt(3) == 'I' || msg.charAt(3) == 'i')
                && (msg.charAt(4) == 'N' || msg.charAt(4) == 'n');
    }

    private static boolean isCommit(String msg) {
        return msg != null
                && msg.length() > 6
                && (msg.charAt(0) == 'C' || msg.charAt(0) == 'c')
                && (msg.charAt(1) == 'O' || msg.charAt(1) == 'o')
                && (msg.charAt(2) == 'M' || msg.charAt(2) == 'm')
                && (msg.charAt(3) == 'M' || msg.charAt(3) == 'm')
                && (msg.charAt(4) == 'I' || msg.charAt(4) == 'i')
                && (msg.charAt(5) == 'T' || msg.charAt(5) == 't');
    }

	public SyncEventContext parse(String eventMessage) {
		if (isBegin(eventMessage) || isCommit(eventMessage)) {
			return null;
		}
		
		return parseEvent(eventMessage);
	}

    private SyncEventContext parseEvent(String message) {
        SyncEventContext event = new SyncEventContext();
        Lexer lexer = new Lexer(message);

        // "table"
        lexer.nextToken(' ');
        // schema_name
        lexer.nextToken('.');
        String schema = lexer.token();
        // table_name
        String table = lexer.nextToken(':');
        lexer.skip(1);
        // event_type
        String eventType = lexer.nextToken(':');

        event.setSchema(schema.replace("\"", ""));
        event.setTable(table.replace("\"", ""));
        event.setEventType(EventTypeEnum.getEventType(eventType));
        lexer.skip(1);

		if (event.getEventType() != EventTypeEnum.TRUNCATE) {
			while (lexer.hasNext()) {
				FieldData data = new FieldData();
				String name = parseName(lexer);
				if ("(no-tuple-data)".equals(name)) {
					// 删除时,无主键,不能同步
					return null;
				}
				String type = parseType(lexer);
				lexer.skip(1);
				String value = parseValue(lexer);

				data.setName(name.replace("\"", ""));
				data.setDataType(type);
				data.setValue(value);
				event.getDatas().add(data);
			}
		}
        return event;
    }

    private String parseName(Lexer lexer) {
        if (lexer.current() == ' ') {
            lexer.skip(1);
        }
        lexer.nextToken('[');
        String name= lexer.token();
        return name.replace("\"", "");
    }

    private String parseType(Lexer lexer) {
        lexer.nextToken(']');
        return lexer.token();
    }

    private String parseValue(Lexer lexer) {
        if (lexer.current() == '\'') {
            lexer.skip(1);
            lexer.nextTokenToQuote();
            return lexer.token();
        }
        lexer.nextToken(' ');
		if (lexer.token.equals("null")) {
			return null;
		} else {
			return lexer.token();
		}
    }

    private static class Lexer {
        private final String input;
        private final char[] array;
        private final int length;
        private int pos = 0;
        private String token;

        public Lexer(String input) {
            this.input = input;
            this.array = input.toCharArray();
            this.length = this.array.length;
        }

        public String token() {
            return token;
        }

        public String nextToken(char comma) {
            if (pos < length) {
                StringBuilder out = new StringBuilder(16);
                while (pos < length && array[pos] != comma) {
                    out.append(array[pos]);
                    pos++;
                }
                pos++;
                return token = out.toString();
            }
            return token = null;
        }

        public String nextTokenToQuote() {
            if (pos < length) {
                int commaCount = 1;
                StringBuilder out = new StringBuilder(16);
                while (!((pos == length - 1 || (array[pos + 1] == ' ' && commaCount % 2 == 1)) && array[pos] == '\'')) {
                    if(array[pos] == '\'') {
                        commaCount++;
                    }
                    out.append(array[pos]);
                    pos++;
                }
                pos++;
                return token = out.toString();
            }
            return token = null;
        }

        public void skip(int skip) {
            //CERT_03_00 检测和避免整数溢出
//            this.pos += skip;
            this.pos = Math.addExact(this.pos, skip);
        }

        public char current() {
            return array[pos];
        }

        public boolean hasNext() {
            return pos < length;
        }
    }
}
