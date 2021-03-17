/*
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package eu.mavinci.desktop.gui.doublepanel.planemain.tagging.jsonGeotags;

import eu.mavinci.core.obfuscation.IKeepAll;

public class Header implements IKeepAll {
    public String frame_id;
    public int seq;
    public Timestamp stamp;
}
