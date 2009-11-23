/*
 * This file is part of ###PROJECT_NAME###
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.business.materials.entities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.validator.NotEmpty;
import org.navalplanner.business.common.BaseEntity;

/**
 * MaterialCategory entity
 *
 * @author Jacobo Aragunde Perez <jaragunde@igalia.com>
 *
 */
public class MaterialCategory extends BaseEntity {

    @NotEmpty
    private String name;

    private MaterialCategory parent;

    private Set<MaterialCategory> subcategories = new HashSet<MaterialCategory>();

    private Set<Material> materials = new HashSet<Material>();

    // Default constructor, needed by Hibernate
    protected MaterialCategory() {

    }

    public static MaterialCategory create(String name) {
        return (MaterialCategory) create(new MaterialCategory(name));
    }

    protected MaterialCategory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MaterialCategory getParent() {
        return parent;
    }

    public void setParent(MaterialCategory parentId) {
        this.parent = parentId;
    }
}
