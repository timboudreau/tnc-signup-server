/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.signup.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.headers.Headers;
import static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE;
import static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.HEAD;
import com.mastfrog.acteur.preconditions.Authenticated;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.bunyan.Logger;
import static com.mastfrog.signup.server.SignupServer.GUICE_BINDING_POSSIBLE_SIGNUPS;
import com.mastfrog.signup.server.model.Signup;
import com.mastfrog.signup.server.model.Signups;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import com.mastfrog.util.time.TimeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import io.netty.handler.codec.http.HttpMethod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 *
 * @author Tim Boudreau
 */
@HttpCall
@Methods({GET, HEAD})
@PathRegex("^api\\/admin\\/sheet$")
@Authenticated
public class SpreadsheetResource extends Acteur {

    @Inject
    SpreadsheetResource(Signups signups, HttpEvent evt) throws IOException {
        if (HttpMethod.GET.name().equals(evt.method().name())) {
            setResponseBodyWriter(SRW.class);
        }
        long lm = Long.MIN_VALUE;
        for (Path pth : signups) {
            long dt = Files.getLastModifiedTime(pth).toMillis();
            lm = Math.max(lm, dt);
        }
        String host = evt.header(HOST);
        ZonedDateTime lastModified = TimeUtil.fromUnixTimestamp(lm);
        String nm = (host == null ? "" : host) + "signups-" + TimeUtil.toSortableStringFormat(lastModified) + ".xlsx";
        add(LAST_MODIFIED, lastModified);
        add(CONTENT_TYPE, MediaType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        add(Headers.CONTENT_DISPOSITION, "attachment; filename=\"" + nm + "\"");
        setChunked(true);
        ok();
    }

    static final class SRW implements Converter<Signup, Path>, ChannelFutureListener {

        private final ObjectMapper mapper;
        private final List<String> cells = new ArrayList<>();
        private final List<String> kinds;
        private final Signups signups;
        private final Logger logger;

        @Inject
        SRW(Signups signups, ObjectMapper mapper, @Named(GUICE_BINDING_POSSIBLE_SIGNUPS) Set<String> possibilities, @Named("admin") Logger logger) throws Exception {
            this.signups = signups;
            this.mapper = mapper;
            kinds = new ArrayList<>(possibilities);
            Collections.sort(kinds);
            cells.add("Name");
            cells.add("Email");
            for (String poss : possibilities) {
                cells.add(poss);
            }
            cells.add("When");
            cells.add("Email Sent");
            cells.add("Email Address Verified");
            this.logger = logger;
        }

        @Override
        public Signup convert(Path r) {
            try {
                return mapper.readValue(Files.readAllBytes(r), Signup.class);
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        }

        @Override
        public Path unconvert(Signup t) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void operationComplete(ChannelFuture f) throws Exception {
            if (f.cause() != null) {
                f.cause().printStackTrace();
                logger.warn("spreadsheet").add(f.cause());
                return;
            }
            Workbook workbook = new XSSFWorkbook();
            CreationHelper createHelper = workbook.getCreationHelper();
            Sheet sheet = workbook.createSheet("Signups");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 14);
            headerFont.setColor(IndexedColors.RED.getIndex());

            // Create a CellStyle with the font
            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < cells.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(cells.get(i));
                cell.setCellStyle(headerCellStyle);
            }
            CellStyle dateCellStyle = workbook.createCellStyle();
            dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-MM-yyyy"));
            Iterator<Signup> it = CollectionUtils.convertedIterator(this, signups.iterator());
            int ix = 0;
            long lm = Long.MIN_VALUE;
            while (it.hasNext()) {
                int cellIx = 0;
                Signup s = it.next();
                Row row = sheet.createRow(ix++);
                lm = Math.max(lm, s.when);
                row.createCell(cellIx++).setCellValue(s.info.name == null ? "" : s.info.name);
                row.createCell(cellIx++).setCellValue(s.info.emailAddress);
                for (String poss : kinds) {
                    row.createCell(cellIx++).setCellValue(s.info.signedUpFor.contains(poss));
                }
                Cell dateCell = row.createCell(cellIx++);
                dateCell.setCellStyle(dateCellStyle);
                dateCell.setCellValue(new Date(s.when));
                row.createCell(cellIx++).setCellValue(s.emailed);
                row.createCell(cellIx++).setCellValue(s.validated);
            }
            // Resize all columns to fit the content size
            for (int i = 0; i < cells.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            ByteBuf buf = f.channel().alloc().ioBuffer();
            try (ByteBufOutputStream o = new ByteBufOutputStream(buf)) {
                workbook.write(o);
            }
            f = f.channel().writeAndFlush(new DefaultHttpContent(buf)).addListener((ChannelFuture ff) -> {
                if (ff.cause() != null) {
                    ff.cause().printStackTrace();
                    logger.warn("spreadsheet").add(ff.cause()).close();
                    return;
                }
                ff.channel().writeAndFlush(DefaultLastHttpContent.EMPTY_LAST_CONTENT);
            });
        }
    }
}
